const displayScoreSelect = () => {
    if (Alteryx.Gui.Manager.getDataItem('IsClassification').getValue()) {
        document.getElementById('scoreclassification').style.display = 'initial'
        document.getElementById('scoreregression').style.display = 'none'
    } else {
        document.getElementById('scoreclassification').style.display = 'none'
        document.getElementById('scoreregression').style.display = 'initial'
    }
}

Alteryx.Gui.BeforeLoad = function (manager, AlteryxDataItems, json) {

    const operationClassificationSelector = new AlteryxDataItems.StringSelector('ClassificationDropdown', {
        optionList: [
            {label: 'True', value: 'True'},
            {label: 'False', value: 'False'}
        ]
    })

    const operationTimeSeriesSelector = new AlteryxDataItems.StringSelector('TimeSeriesDropdown', {
        optionList: [
            {label: 'True', value: 'True'},
            {label: 'False', value: 'False'}
        ]
    })

    Alteryx.Gui.Manager.getDataItem('IsTimeSeries').setValue('False')
    manager.addDataItem(operationTimeSeriesSelector)
    manager.bindDataItemToWidget(operationTimeSeriesSelector, 'TimeSeriesDropdown')
    displayScoreSelect()
}

Alteryx.Gui.AfterLoad = function (manager) {
    displayScoreSelect()
    Alteryx.Gui.Manager.getDataItem('IsClassification').registerPropertyListener('value', displayScoreSelect)
}